import controllers.Application
import play.api.Application
import play.api.GlobalSettings
import play.api.Logger

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Setting AKKA scheduler on start")
    Application.setTweetInterval(Application.currentTweetInterval)
  }

}