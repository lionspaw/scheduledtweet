package models

import play.api.db._
import play.api.Play.current
import anorm._
import anorm.SqlParser._
import controllers.Application
import org.joda.time.DateTime

case class TweetsDTO(
  id: Option[Long],
  text: String,
  path: String,
  state: Int,
  date: String
)

object Tweets {
  val STATE_UPCOMING = 0
  val STATE_TWEETED = 1
  val STATE_DELETED = 2
  
  val simple = {
      get[Long]("id") ~
      get[String]("text") ~
      get[String]("path") ~
      get[Int]("state") ~
      get[String]("date") map {
      case id~text~path~state~date => TweetsDTO(Some(id), text, path, state, date)
      }
  }

  def create(tweet: TweetsDTO): Int = {
    DB.withConnection { implicit connection =>
    SQL(
        """
        insert into tweets (text, path, state, date)
        values ({text}, {path}, {state}, {date})
        """
        ).on(
        'text -> tweet.text,
        'path -> tweet.path,
        'state -> tweet.state,
        'date -> tweet.date
        ).executeUpdate()
    }
  }
  
  def findAllUpcoming(): Seq[TweetsDTO] = {
    DB.withConnection { implicit connection =>
      SQL("select * from tweets where state = {state}")
      .on('state -> STATE_UPCOMING)
      .as(simple.*)
    }
  }
  
  def getLastTweet(): Option[TweetsDTO] = {
    DB.withConnection { implicit connection =>
      SQL("select * from tweets where state = {state} order by id desc limit 1")
      .on('state -> STATE_TWEETED)
      .as(simple.singleOpt)
    }
  }
  
  def findFirstUpcoming(): Option[TweetsDTO] = {
    DB.withConnection { implicit connection =>
      SQL("select * from tweets where state = {state} order by id asc limit 1")
      .on('state -> STATE_UPCOMING)
      .as(simple.singleOpt)
    }
  }
  
  def updateState(id: Long, state: Int) {
    DB.withConnection { implicit connection =>
      SQL("update tweets set state = {state}, date = {date} where id = {id}").on(
        'state -> state,
        'date -> Application.formatDateWithTime(new DateTime),
        'id -> id
      ).executeUpdate()
    }
  }
  
}
