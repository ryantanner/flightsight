import play.api._

import models._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")

    Logger.debug(Airline.AirlineTable.ddl.createStatements.mkString("\n"))
  }  
  

    
}
