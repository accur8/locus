package a8.locus.model


import a8.shared.app.Logging
import a8.shared.SharedImports._
import a8.shared.jdbcf.mapper.{PK, SqlTable}
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.json.ast.JsDoc

object Data extends Logging {

  object OrganizationUser extends DataMixins.OrganizationUserObjectMixin {
//    lazy val mapper = m3.jdbc.managed_mapper.Mapper[OrganizationUser, Data.OrganizationUser.Uid]
//    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
//      val uid = QueryDsl.field[Data.OrganizationUser.Uid]("uid", join)
//      val userGroupUid = QueryDsl.field[a8.locus.model.Uid]("userGroupUid", join)
//      val permissions = QueryDsl.field[Json]("permissions", join)
//      val visible = QueryDsl.field[Boolean]("visible", join)
//      val extraConfig = QueryDsl.field[Json]("extraConfig", join)
//      val audit_version = QueryDsl.field[Long]("audit_version", join)
//      val audit_userGroupUid = QueryDsl.field[a8.locus.model.Uid]("audit_userGroupUid", join)
//      val organizationUid = QueryDsl.field[Data.Organization.Uid]("organizationUid", join)
//      lazy val organization: Organization.TableDsl = {
//        val childJoin = QueryDsl.createJoin(join, "organization", OrganizationUser.queryDsl.tableDsl, ()=>organization, Organization.mapper) { (from,to) =>
//         from.organizationUid === to.uid
//        }
//        new Organization.TableDsl(childJoin)
//      }
//    }
//    val queryDsl = new QueryDsl[OrganizationUser, TableDsl](mapper, new TableDsl)
//    def query(whereFn: TableDsl => QueryDsl.Condition): querydsl.SelectQuery[OrganizationUser, TableDsl] = queryDsl.query(whereFn)
//    def update(set: TableDsl => Iterable[querydsl.UpdateQuery.Assignment[_]]): querydsl.UpdateQuery[TableDsl] = queryDsl.update(set)
  }
  
  @SqlTable(name="OrganizationUser")
  case class OrganizationUser(
    @PK uid: Data.OrganizationUser.Uid = Data.OrganizationUser.Uid.random(),
    userGroupUid: a8.locus.model.Uid,
    permissions: JsDoc,
    visible: Boolean = true,
    extraConfig: Option[JsDoc] = None,
    /*@ReadOnly*/ audit_version: Option[Long] = None,
    /*@ReadOnly*/ audit_userGroupUid: Option[a8.locus.model.Uid] = None,
    organizationUid: Data.Organization.Uid,
  ) extends DataMixins.OrganizationUserMixin
  
  object Repository extends DataMixins.RepositoryObjectMixin {
//    lazy val mapper = m3.jdbc.managed_mapper.Mapper[Repository, Data.Repository.Uid]
//    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
//      val uid = QueryDsl.field[Data.Repository.Uid]("uid", join)
//      val name = QueryDsl.field[String]("name", join)
//      val kind = QueryDsl.field[String]("kind", join)
//      val packageKind = QueryDsl.field[String]("packageKind", join)
//      val visible = QueryDsl.field[Boolean]("visible", join)
//      val extraConfig = QueryDsl.field[Json]("extraConfig", join)
//      val audit_version = QueryDsl.field[Long]("audit_version", join)
//      val audit_userGroupUid = QueryDsl.field[a8.locus.model.Uid]("audit_userGroupUid", join)
//      val organizationUid = QueryDsl.field[Data.Organization.Uid]("organizationUid", join)
//      lazy val organization: Organization.TableDsl = {
//        val childJoin = QueryDsl.createJoin(join, "organization", Repository.queryDsl.tableDsl, ()=>organization, Organization.mapper) { (from,to) =>
//         from.organizationUid === to.uid
//        }
//        new Organization.TableDsl(childJoin)
//      }
//    }
//    val queryDsl = new QueryDsl[Repository, TableDsl](mapper, new TableDsl)
//    def query(whereFn: TableDsl => QueryDsl.Condition): querydsl.SelectQuery[Repository, TableDsl] = queryDsl.query(whereFn)
//    def update(set: TableDsl => Iterable[querydsl.UpdateQuery.Assignment[_]]): querydsl.UpdateQuery[TableDsl] = queryDsl.update(set)
  }
  
  @SqlTable(name="Repository")
  case class Repository(
    @PK uid: Data.Repository.Uid = Data.Repository.Uid.random(),
    name: String,
    kind: String,
    packageKind: String,
    visible: Boolean = true,
    extraConfig: Option[JsDoc] = None,
    /*@ReadOnly*/ audit_version: Option[Long] = None,
    /*@ReadOnly*/ audit_userGroupUid: Option[a8.locus.model.Uid] = None,
    organizationUid: Data.Organization.Uid,
  ) extends DataMixins.RepositoryMixin
  
  object Organization extends DataMixins.OrganizationObjectMixin {
//    lazy val mapper = m3.jdbc.managed_mapper.Mapper[Organization, Data.Organization.Uid]
//    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
//      val uid = QueryDsl.field[Data.Organization.Uid]("uid", join)
//      val name = QueryDsl.field[String]("name", join)
//      val description = QueryDsl.field[String]("description", join)
//      val visible = QueryDsl.field[Boolean]("visible", join)
//      val extraConfig = QueryDsl.field[Json]("extraConfig", join)
//      val audit_version = QueryDsl.field[Long]("audit_version", join)
//      val audit_userGroupUid = QueryDsl.field[a8.locus.model.Uid]("audit_userGroupUid", join)
//    }
//    val queryDsl = new QueryDsl[Organization, TableDsl](mapper, new TableDsl)
//    def query(whereFn: TableDsl => QueryDsl.Condition): querydsl.SelectQuery[Organization, TableDsl] = queryDsl.query(whereFn)
//    def update(set: TableDsl => Iterable[querydsl.UpdateQuery.Assignment[_]]): querydsl.UpdateQuery[TableDsl] = queryDsl.update(set)
  }
  
  @SqlTable(name="Organization")
  case class Organization(
    @PK uid: Data.Organization.Uid = Data.Organization.Uid.random(),
    name: String,
    description: String,
    visible: Boolean = true,
    extraConfig: Option[JsDoc] = None,
    /*@ReadOnly*/ audit_version: Option[Long] = None,
    /*@ReadOnly*/ audit_userGroupUid: Option[a8.locus.model.Uid] = None,
  ) extends DataMixins.OrganizationMixin
  
  object RepositoryEvent extends DataMixins.RepositoryEventObjectMixin {
//    lazy val mapper = m3.jdbc.managed_mapper.Mapper[RepositoryEvent, Data.RepositoryEvent.Uid]
//    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
//      val uid = QueryDsl.field[Data.RepositoryEvent.Uid]("uid", join)
//      val kind = QueryDsl.field[String]("kind", join)
//      val path = QueryDsl.field[String]("path", join)
//      val userGroupUid = QueryDsl.field[a8.locus.model.Uid]("userGroupUid", join)
//      val size = QueryDsl.field[Long]("size", join)
//      val ipAddress = QueryDsl.field[String]("ipAddress", join)
//      val userAgent = QueryDsl.field[String]("userAgent", join)
//      val created = QueryDsl.field[DateTime]("created", join)
//      val extraConfig = QueryDsl.field[Json]("extraConfig", join)
//      val repositoryUid = QueryDsl.field[Data.Repository.Uid]("repositoryUid", join)
//      lazy val repository: Repository.TableDsl = {
//        val childJoin = QueryDsl.createJoin(join, "repository", RepositoryEvent.queryDsl.tableDsl, ()=>repository, Repository.mapper) { (from,to) =>
//         from.repositoryUid === to.uid
//        }
//        new Repository.TableDsl(childJoin)
//      }
//    }
//    val queryDsl = new QueryDsl[RepositoryEvent, TableDsl](mapper, new TableDsl)
//    def query(whereFn: TableDsl => QueryDsl.Condition): querydsl.SelectQuery[RepositoryEvent, TableDsl] = queryDsl.query(whereFn)
//    def update(set: TableDsl => Iterable[querydsl.UpdateQuery.Assignment[_]]): querydsl.UpdateQuery[TableDsl] = queryDsl.update(set)
  }
  
  @SqlTable(name="RepositoryEvent")
  case class RepositoryEvent(
    @PK uid: Data.RepositoryEvent.Uid = Data.RepositoryEvent.Uid.random(),
    kind: String,
    path: String,
    userGroupUid: a8.locus.model.Uid,
    size: Long,
    ipAddress: Option[String],
    userAgent: Option[String],
    created: DateTime,
    extraConfig: Option[JsDoc] = None,
    repositoryUid: Data.Repository.Uid,
  ) extends DataMixins.RepositoryEventMixin
  
  object RepositoryPermission extends DataMixins.RepositoryPermissionObjectMixin {
//    lazy val mapper = m3.jdbc.managed_mapper.Mapper[RepositoryPermission, Data.RepositoryPermission.Uid]
//    class TableDsl(join: QueryDsl.Join = QueryDsl.RootJoin) {
//      val uid = QueryDsl.field[Data.RepositoryPermission.Uid]("uid", join)
//      val userGroupUid = QueryDsl.field[a8.locus.model.Uid]("userGroupUid", join)
//      val kind = QueryDsl.field[String]("kind", join)
//      val visible = QueryDsl.field[Boolean]("visible", join)
//      val extraConfig = QueryDsl.field[Json]("extraConfig", join)
//      val audit_version = QueryDsl.field[Long]("audit_version", join)
//      val audit_userGroupUid = QueryDsl.field[a8.locus.model.Uid]("audit_userGroupUid", join)
//      val repositoryUid = QueryDsl.field[Data.Repository.Uid]("repositoryUid", join)
//      lazy val repository: Repository.TableDsl = {
//        val childJoin = QueryDsl.createJoin(join, "repository", RepositoryPermission.queryDsl.tableDsl, ()=>repository, Repository.mapper) { (from,to) =>
//         from.repositoryUid === to.uid
//        }
//        new Repository.TableDsl(childJoin)
//      }
//    }
//    val queryDsl = new QueryDsl[RepositoryPermission, TableDsl](mapper, new TableDsl)
//    def query(whereFn: TableDsl => QueryDsl.Condition): querydsl.SelectQuery[RepositoryPermission, TableDsl] = queryDsl.query(whereFn)
//    def update(set: TableDsl => Iterable[querydsl.UpdateQuery.Assignment[_]]): querydsl.UpdateQuery[TableDsl] = queryDsl.update(set)
  }
  
  @SqlTable(name="RepositoryPermission")
  case class RepositoryPermission(
    @PK uid: Data.RepositoryPermission.Uid = Data.RepositoryPermission.Uid.random(),
    userGroupUid: a8.locus.model.Uid,
    kind: String,
    visible: Boolean = true,
    extraConfig: JsDoc = JsDoc.empty,
    /*@ReadOnly*/ audit_version: Option[Long] = None,
    /*@ReadOnly*/ audit_userGroupUid: Option[a8.locus.model.Uid] = None,
    repositoryUid: Data.Repository.Uid,
  ) extends DataMixins.RepositoryPermissionMixin
}