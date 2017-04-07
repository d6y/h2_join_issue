import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Example {

  // People have Addresses
  // People have Roles at zero or more Address (e.g., employee, home owner...)
  // Roles have a set of duties
  case class Person(id: Int, name: String, age: Int, addressId: Int)
  class People(tag: Tag) extends Table[Person](tag, "PERSON") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def age = column[Int]("AGE")
    def addressId = column[Int]("ADDRESS_ID")
    def * = (id,name,age,addressId).mapTo[Person]
    def address = foreignKey("ADDRESS",addressId,addresses)(_.id)
  }
  lazy val people = TableQuery[People]

  case class Address(id: Int, street: String, city: String)
  class Addresses(tag: Tag) extends Table[Address](tag, "ADDRESS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def street = column[String]("STREET")
    def city = column[String]("CITY")
    def * = (id,street,city).mapTo[Address]
  }
  lazy val addresses = TableQuery[Addresses]

  case class Role(id: Int, personId: Int, addressId: Int)
  class Roles(tag: Tag) extends Table[Role](tag, "ROLE_AT_ADDRESS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def personId = column[Int]("PERSON_ID")
    def addressId = column[Int]("ADDRESS_ID")
    def * = (id, personId, addressId).mapTo[Role]
    def person = foreignKey("PERSON_ROLE_FK",personId,people)(_.id)
    def address = foreignKey("ADDRESS_ROLE_FK",addressId,addresses)(_.id)
  }
  lazy val roles = TableQuery[Roles]

  case class Duty(roleId: Int, duty: String)
  class Duties(tag: Tag) extends Table[Duty](tag, "DUTY") {
    def roleId = column[Int]("ROLE_ID")
    def duty = column[String]("DUTY_NAME")
    def * = (roleId, duty).mapTo[Duty]
    def role = foreignKey("DUTY_ROLE_FK",roleId,roles)(_.id)
  }
  lazy val duties = TableQuery[Duties]


  val q1 = addresses joinLeft people on (_.id === _.addressId)
  val q2 = q1 joinLeft roles on { case ( (a, p), r ) => p.map(_.id) === r.personId }
  val q3 = q2 joinLeft duties on { case ( ((a,p), r), d ) => r.map(_.id) === d.roleId }

  def main(args: Array[String]): Unit = {

    println(q1.result.statements)
    println(q2.result.statements)
    println(q3.result.statements)

    val program = for {
      _ <- (people.schema ++ addresses.schema ++ roles.schema ++ duties.schema).create
      officeId <- addresses returning addresses.map(_.id) += Address(0,"Street","City")
      aliceId <- people returning people.map(_.id) += Person(0,"Alice",30,officeId)
      _ <- people += Person(0,"Bob",30,officeId)
      bossId <- roles returning roles.map(_.id) += Role(0, aliceId, officeId)
      _ <- duties += Duty(bossId, "pay everyone")
      _ <- duties += Duty(bossId, "hire and fire")
      rows <- q3.result 
    } yield rows

    val db = Database.forConfig("example")
    try Await.result(db.run(program), 8.seconds).foreach(println) finally db.close
  }
}
