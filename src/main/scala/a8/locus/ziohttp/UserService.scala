package a8.locus.ziohttp


import a8.locus.Config.{User, UserPrivilege}
import a8.locus.ziohttp.ZHttpHandler.*

trait UserService {
  def resolve(request: Request): Option[User]
  def requirePrivilege(priv: UserPrivilege): M[Unit]
}
