package a8.locus.model

import java.sql.ResultSet
import a8.locus.model.Data.{Organization, OrganizationUser, Repository, RepositoryEvent, RepositoryPermission}
import a8.shared.json.JsonCodec

object DataMixins {


  case class InMemoryContainer(
    organizationUsers: Iterable[OrganizationUser],
    organizations: Iterable[Organization],
    repositories: Iterable[Repository],
    repositoryPermissions: Iterable[RepositoryPermission],
  ) extends Container {

    lazy val organizationsByUid: Map[Data.Organization.Uid, Organization] =
      organizations
        .iterator
        .map(o => o.uid -> o)
        .toMap

    lazy val repositoriesByOrganization: Map[Data.Organization.Uid, Iterable[Repository]] =
      repositories
        .groupBy(_.organizationUid)

    lazy val userByOrganization: Map[Data.Organization.Uid, Iterable[OrganizationUser]] =
      organizationUsers
        .groupBy(_.organizationUid)

    override def repositories(uid: Data.Organization.Uid): Iterable[Repository] =
      repositoriesByOrganization
        .getOrElse(uid, Iterable.empty)

    override def users(uid: Data.Organization.Uid): Iterable[OrganizationUser] =
      userByOrganization
        .getOrElse(uid, Iterable.empty)

    override def organization(organizationUid: Data.Organization.Uid): Organization =
      organizationsByUid(organizationUid)

    override def permissions(uid: Data.Repository.Uid): Iterable[RepositoryPermission] = ???
  }

  trait Container {
    def repositories(uid: Data.Organization.Uid): Iterable[Repository]
    def users(uid: Data.Organization.Uid): Iterable[OrganizationUser]
    def organization(organizationUid: Data.Organization.Uid): Organization
    def permissions(uid: Data.Repository.Uid): Iterable[RepositoryPermission]
  }

  trait TUid[A] {
    def value: String
  }

  trait BaseObjectMixin[A] {
    object Uid {

      def random(): Uid = {
        val uidStr = java.util.UUID.randomUUID.toString.replace("-","")
        new Uid(uidStr)
      }

      implicit val format =
        JsonCodec.string.dimap[Uid](
          Uid.apply,
          _.value,
        )

    }
    case class Uid(value: String) extends TUid[A]
  }



  trait RepositoryObjectMixin extends BaseObjectMixin[Data.Repository]

  trait RepositoryMixin { self: Repository =>

    def permissions(implicit container: Container): Iterable[RepositoryPermission] =
      container.permissions(self.uid)

    def organization(implicit container: Container): Organization =
      container.organization(self.organizationUid)

  }


  trait OrganizationUserObjectMixin extends BaseObjectMixin[Data.OrganizationUser]

  trait OrganizationUserMixin { self: OrganizationUser =>

    def organization(implicit container: Container): Organization =
      container.organization(self.organizationUid)

  }


  trait OrganizationObjectMixin extends BaseObjectMixin[Data.Organization]

  trait OrganizationMixin { self: Organization =>

    def users(implicit container: Container): Iterable[OrganizationUser] =
      container.users(self.uid)

    def repositories(implicit container: Container): Iterable[Repository] =
      container.repositories(self.uid)

  }

  trait RepositoryEventObjectMixin extends BaseObjectMixin[Data.RepositoryEvent]
  trait RepositoryEventMixin { self: RepositoryEvent =>
  }

  trait RepositoryPermissionObjectMixin extends BaseObjectMixin[Data.RepositoryPermission]
  trait RepositoryPermissionMixin { self: RepositoryPermission =>
  }


}
