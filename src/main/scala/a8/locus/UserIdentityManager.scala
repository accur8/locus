package a8.locus

import io.undertow.security.idm.Account
import io.undertow.security.idm.Credential
import io.undertow.security.idm.IdentityManager
import io.undertow.security.idm.PasswordCredential
import java.security.Principal
import java.util

import cats.data.Chain
import SharedImports._

/**
  * A simple {@link IdentityManager} implementation, that just takes a map of users to their
  * password.
  */
case class UserIdentityManager(users: Iterable[Config.User]) extends IdentityManager {

  lazy val usersById: Map[String, Config.User] =
    users
      .map(u => u.name -> u)
      .iterator
      .toMap

  override def verify(account: Account): Account = { // An existing account so for testing assume still valid.
    account
  }

  override def verify(id: String, credential: Credential): Account = {
    val account = getAccount(id)
    if (account != null && verifyCredential(account, credential)) return account
    null
  }

  override def verify(credential: Credential): Account = { // TODO Auto-generated method stub
    null
  }

  private def verifyCredential(account: Account, credential: Credential): Boolean = {
    credential match {
      case pc: PasswordCredential =>
        val password: String = new String(pc.getPassword)
        usersById
          .get(account.getPrincipal.getName)
          .exists(_.password === password)
      case _ =>
        false
    }
  }

  case class UserAsPrinciple(user: Config.User) extends Principal  {
    override def getName: String = user.name
  }

  case class UserAsAccount(user: Config.User) extends Account {
    lazy val principle: UserAsPrinciple = UserAsPrinciple(user)
    override def getPrincipal: Principal = principle
    override def getRoles: util.Set[String] = java.util.Collections.emptySet()
  }

  private def getAccount(id: String): Account =
    usersById
      .get(id)
      .map(UserAsAccount.apply)
      .orNull

}