package controllers

import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import scala.util.Random
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import models.Tweets
import models.TweetsDTO
import play.api.data.Form
import play.api.data.Forms._
import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Results
import play.api.mvc.Result
import play.api.mvc.Security
import play.api.mvc.AnyContent
import scala.concurrent.duration.FiniteDuration
import twitter4j.conf.ConfigurationBuilder
import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.StatusUpdate
import play.api.libs.concurrent.Akka
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import twitter4j.StatusUpdate
import akka.actor.Cancellable
import org.mindrot.jbcrypt.BCrypt


object Application extends Controller with Secured {

  def index = withAuth { password => implicit request =>
    Ok(views.html.index(currentTweetInterval, Tweets.getLastTweet(), Tweets.findAllUpcoming()))
  }
  
  val BASE_DIR = new File(".").getCanonicalPath()
  val BASE_PIC = BASE_DIR + "/PICTURES" //directory to place pictures for generating new tweets
  val BASE_TWEET = BASE_DIR + "/TWEETPICS/" //direcotry to place pictures after generating, used to send tweets with media
  
  /*
   * Generates tweet candidates from BASE_PIC directory
   * Text of the tweet is directory name, media is picture from each directory
   */
  def generateTweets = withAuth { password => implicit request =>
    
    val random: SecureRandom = new SecureRandom();
    val currentDate = DateTime.now()
    var newDirName: String = ""
    //generate not existing directory name
    do {
      val randomString: String = new BigInteger(130, random).toString(10);
      newDirName = formatDate(currentDate) + "." + randomString
    } while(new File(BASE_TWEET + newDirName).isDirectory())
    //copy files from BASE_PIC to new directory
    FileUtils.copyDirectory(new File(BASE_PIC), new File(BASE_TWEET + newDirName))
    
    val photographs = new File(BASE_TWEET + newDirName).listFiles //get photographer directories
    var counter:Int = 0
    var tweets: List[(String, String)] = Nil
    for (photoman <- photographs) {
      val name = photoman.getName
      val pictures = photoman.listFiles()
      //generate list uf tupples (author, path)
      pictures.foreach(x => tweets = (name, newDirName + "/" + name + "/" + x.getName()) :: tweets)
    }
    tweets = Random.shuffle(tweets)
    //insert tweet candidates into db table 
    tweets.foreach(tweet => Tweets.create(TweetsDTO(None, tweet._1, tweet._2, Tweets.STATE_UPCOMING, "")))
    
    val msg = "Added " + tweets.size + " tweet candidates"
    Logger.info(msg + " at " + Application.formatDateWithTime(new DateTime))
    
    Redirect(routes.Application.index).flashing("msg" -> msg)
  }
  
  def formatDate(dateTime: DateTime): String = {
    dateTime.getDayOfMonth() + "." + dateTime.getMonthOfYear() + "." + dateTime.getYear()
  }
  
  def formatDateWithTime(dateTime: DateTime): String = {
    formatDate(dateTime) + " " + dateTime.getHourOfDay() + ":" + dateTime.getMinuteOfHour() + ":" + dateTime.getSecondOfMinute()
  }
  
  /*
   * Login for basic security
   */
  case class LoginClass(pass: String)
  
  val loginForm = Form(
    mapping(
        "password" -> text
    )(LoginClass.apply)(LoginClass.unapply)  
  )
  
  def showLogin = Action {
    Ok(views.html.login(loginForm))
  }
  
  def logout = withAuth { password => implicit request =>
    Redirect(routes.Application.showLogin).withNewSession
  }
  
  def login = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      errors => BadRequest(views.html.login(errors)),
      form => {
        //Logger.info(BCrypt.hashpw("password", BCrypt.gensalt())) Hashing a password
        if (BCrypt.checkpw(form.pass, "$2a$10$uTAQqv3ucWBuwuIW4uw9juzvOsknXPVuoOkLCPbwdnjXhcMcwAfZ.")) {
          Redirect(routes.Application.index).withSession("password" -> form.pass)
        } else {
          Ok(views.html.login(loginForm))
        }
      } 
    )
  }

  def setIntervalFromBrowser(interval: Int) = withAuth { password => implicit request =>
    setTweetInterval(interval)
    val msg = "Interval changed to " + interval
    Logger.info(msg + " at " + Application.formatDateWithTime(new DateTime))
    Redirect(routes.Application.index).flashing("msg" -> msg)
  } 
  
  //Twitter configuration
  val cb: ConfigurationBuilder = new ConfigurationBuilder();
  //Get these keys from apps.twitter.com
  cb.setDebugEnabled(true)
    .setOAuthConsumerKey("YOUR.CONSUMER.KEY")
    .setOAuthConsumerSecret("YOUR.CONSUMER.SECRET")
    .setOAuthAccessToken("YOUR.ACCESS.TOKEN")
    .setOAuthAccessTokenSecret("YOUR.ACCESS.TOKEN.SECRET");
  val tf: TwitterFactory = new TwitterFactory(cb.build());
  val twitter: Twitter = tf.getInstance();
  
  var currentTweetInterval: Int = 6
  var cancellableScheduler: Cancellable = null

  //create scheduler for tweeting
  def setTweetInterval(interval: Int) {
    if (cancellableScheduler != null) cancellableScheduler.cancel
    
    //Generate tweet
    currentTweetInterval = interval
    cancellableScheduler = Akka.system.scheduler.schedule(
        new FiniteDuration(0, TimeUnit.SECONDS),
        new FiniteDuration(currentTweetInterval, TimeUnit.HOURS)) {
      val tweetOpt = Tweets.findFirstUpcoming()
      if (tweetOpt.isDefined) {
        val tweet = tweetOpt.get
        val tweetStatus = new StatusUpdate("Photo by " + tweet.text)
        tweetStatus.setMedia(new File(Application.BASE_TWEET + tweet.path))
        //twitter.updateStatus(tweetStatus)
        //update state after tweeting
        Tweets.updateState(tweet.id.get, Tweets.STATE_TWEETED)
        Logger.info("Tweeted id " + tweet.id.get + " at " + Application.formatDateWithTime(new DateTime))
      } else {
        Logger.info("Nothing to tweet at " + Application.formatDateWithTime(new DateTime))
      }
    }
  }
  
}

trait Secured {
  def username(request: RequestHeader) = request.session.get("password")
      
  def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)
  
  def withAuth(f: => String => Request[AnyContent] => Result) = {
      Security.Authenticated(username, onUnauthorized) { user =>
      Action(request => f(user)(request))
    }
  }
}