package controllers

import java.io.File
import scala.concurrent.Future
import play.api.libs.Files.TemporaryFile
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Action
import play.api.mvc.Controller
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.net.URLDecoder

object ImageHandler extends Controller {
  
  /**
   * Action to read an image from a predefined place from filesystem
   */
  def getImage(picture: String) = Action.async {
    
    val futureImg: Future[java.io.File] = scala.concurrent.Future {
      new java.io.File(Application.BASE_TWEET + URLDecoder.decode(picture, "UTF-8"))
    }
    futureImg.map(img => Ok.sendFile(content = img))
  }
  
}