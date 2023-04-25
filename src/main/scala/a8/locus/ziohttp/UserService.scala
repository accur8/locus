package a8.locus.ziohttp


import a8.locus.Config.{LocusConfig, SubnetManager, User, UserPrivilege}
import zio.http.{Header, Headers, Request}
import a8.locus.ziohttp.model.*
import a8.locus.SharedImports.*
import zio.{ULayer, ZLayer}
import a8.locus.ResolvedModel
import zio.http.Header.WWWAuthenticate

object UserService {

  val layer: zio.ZLayer[LocusConfig & ResolvedModel & SubnetManager, Nothing, UserService] =
    ZLayer.fromZIO(
      for {
        config <- zservice[LocusConfig]
        resolvedModel <- zservice[ResolvedModel]
        anonymousSubnetManager <- zservice[SubnetManager]
      } yield UserServiceImpl(config, resolvedModel, anonymousSubnetManager)
    )

  case class UserServiceImpl(config: LocusConfig, resolvedModel: ResolvedModel, anonymousSubnetManager: SubnetManager) extends UserService {

    override def requirePrivilege(priv: UserPrivilege, request: Request): M[Unit] =
      resolveUser(request)
        .map {
          _.hasPrivilege(priv) match {
            case true =>
              zunit
            case false =>
              zfail(requireAuthenticationResponse)
          }
        }
        .getOrElse(zfail(requireAuthenticationResponse))

    def anonymousLogin(request: Request): Option[User] =
      for {
        remoteAddress <- request.remoteAddress
        user <-
          anonymousSubnetManager
            .isInSubnet(remoteAddress, request.rawHeader("X-Forwarded-For"))
            .toOption(User.anonymous)
      } yield user

    def requireAuthenticationResponse =
      HttpResponseException(
        HttpResponse(
          status = HttpStatusCode.NotAuthorized,
          headers = Headers(
            WWWAuthenticate.Basic(Some(resolvedModel.config.realm))
          )
        )
      )

    def resolveUser(request: Request): Option[User] =
      request
        .rawHeader("Authorization")
        .flatMap(authenticate)
        .orElse(anonymousLogin(request))

    def authenticate(authenticateHeaderValue: String): Option[User] = {
      authenticateHeaderValue.trim.splitList(" ", limit = 2) match {
        case List("Basic", credentialsBase64) =>
          val credentials = new String(java.util.Base64.getDecoder.decode(credentialsBase64))
          credentials.splitList(":", limit = 2) match {
            case List(user, password) =>
              resolvedModel
                .config
                .users
                .find(u => u.name =:= user && u.password =:= password)
            case _ =>
              None
          }
        case _ =>
          None
      }


    }


  }

}


trait UserService {

  def requirePrivilege(priv: UserPrivilege, request: Request): M[Unit]

}
