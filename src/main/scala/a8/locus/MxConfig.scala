package a8.locus

/**

  WARNING THIS IS GENERATED CODE.  DO NOT EDIT.

  The only manually maintained code is the code between the //==== (normally where you add your imports)

*/

//====
import a8.locus.Config._
import SharedImports._
import a8.locus.model.Uri
//====

import a8.shared.Meta.{CaseClassParm, Generator, Constructors}



object MxConfig {
  
  trait MxMultiplexerRepo {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[MultiplexerRepo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[MultiplexerRepo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[MultiplexerRepo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.repos)
          .addField(_.repoForWrites)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[MultiplexerRepo] = zio.prelude.Equal.default
    
    
    
    
    lazy val generator: Generator[MultiplexerRepo,parameters.type] =  {
      val constructors = Constructors[MultiplexerRepo](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[MultiplexerRepo,CiString] = CaseClassParm[MultiplexerRepo,CiString]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val repos: CaseClassParm[MultiplexerRepo,Iterable[CiString]] = CaseClassParm[MultiplexerRepo,Iterable[CiString]]("repos", _.repos, (d,v) => d.copy(repos = v), None, 1)
      lazy val repoForWrites: CaseClassParm[MultiplexerRepo,Option[CiString]] = CaseClassParm[MultiplexerRepo,Option[CiString]]("repoForWrites", _.repoForWrites, (d,v) => d.copy(repoForWrites = v), None, 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): MultiplexerRepo = {
        MultiplexerRepo(
          name = values(0).asInstanceOf[CiString],
          repos = values(1).asInstanceOf[Iterable[CiString]],
          repoForWrites = values(2).asInstanceOf[Option[CiString]],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): MultiplexerRepo = {
        val value =
          MultiplexerRepo(
            name = values.next().asInstanceOf[CiString],
            repos = values.next().asInstanceOf[Iterable[CiString]],
            repoForWrites = values.next().asInstanceOf[Option[CiString]],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: CiString, repos: Iterable[CiString], repoForWrites: Option[CiString]): MultiplexerRepo =
        MultiplexerRepo(name, repos, repoForWrites)
    
    }
    
    
    lazy val typeName = "MultiplexerRepo"
  
  }
  
  
  
  
  trait MxUrlRepo {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[UrlRepo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[UrlRepo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[UrlRepo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.url)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[UrlRepo] = zio.prelude.Equal.default
    
    
    
    
    lazy val generator: Generator[UrlRepo,parameters.type] =  {
      val constructors = Constructors[UrlRepo](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[UrlRepo,CiString] = CaseClassParm[UrlRepo,CiString]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val url: CaseClassParm[UrlRepo,Uri] = CaseClassParm[UrlRepo,Uri]("url", _.url, (d,v) => d.copy(url = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): UrlRepo = {
        UrlRepo(
          name = values(0).asInstanceOf[CiString],
          url = values(1).asInstanceOf[Uri],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): UrlRepo = {
        val value =
          UrlRepo(
            name = values.next().asInstanceOf[CiString],
            url = values.next().asInstanceOf[Uri],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: CiString, url: Uri): UrlRepo =
        UrlRepo(name, url)
    
    }
    
    
    lazy val typeName = "UrlRepo"
  
  }
  
  
  
  
  trait MxLocalRepo {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[LocalRepo,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[LocalRepo,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[LocalRepo,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.directory)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[LocalRepo] = zio.prelude.Equal.default
    
    
    
    
    lazy val generator: Generator[LocalRepo,parameters.type] =  {
      val constructors = Constructors[LocalRepo](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[LocalRepo,CiString] = CaseClassParm[LocalRepo,CiString]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val directory: CaseClassParm[LocalRepo,String] = CaseClassParm[LocalRepo,String]("directory", _.directory, (d,v) => d.copy(directory = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): LocalRepo = {
        LocalRepo(
          name = values(0).asInstanceOf[CiString],
          directory = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): LocalRepo = {
        val value =
          LocalRepo(
            name = values.next().asInstanceOf[CiString],
            directory = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: CiString, directory: String): LocalRepo =
        LocalRepo(name, directory)
    
    }
    
    
    lazy val typeName = "LocalRepo"
  
  }
  
  
  
  
  trait MxS3Config {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[S3Config,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[S3Config,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[S3Config,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.accessKey)
          .addField(_.secretKey)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[S3Config] = zio.prelude.Equal.default
    
    
    
    
    lazy val generator: Generator[S3Config,parameters.type] =  {
      val constructors = Constructors[S3Config](2, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val accessKey: CaseClassParm[S3Config,String] = CaseClassParm[S3Config,String]("accessKey", _.accessKey, (d,v) => d.copy(accessKey = v), None, 0)
      lazy val secretKey: CaseClassParm[S3Config,String] = CaseClassParm[S3Config,String]("secretKey", _.secretKey, (d,v) => d.copy(secretKey = v), None, 1)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): S3Config = {
        S3Config(
          accessKey = values(0).asInstanceOf[String],
          secretKey = values(1).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): S3Config = {
        val value =
          S3Config(
            accessKey = values.next().asInstanceOf[String],
            secretKey = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(accessKey: String, secretKey: String): S3Config =
        S3Config(accessKey, secretKey)
    
    }
    
    
    lazy val typeName = "S3Config"
  
  }
  
  
  
  
  trait MxUser {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[User,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[User,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[User,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.name)
          .addField(_.password)
          .addField(_.privilege)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[User] = zio.prelude.Equal.default
    
    
    
    
    lazy val generator: Generator[User,parameters.type] =  {
      val constructors = Constructors[User](3, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val name: CaseClassParm[User,String] = CaseClassParm[User,String]("name", _.name, (d,v) => d.copy(name = v), None, 0)
      lazy val password: CaseClassParm[User,String] = CaseClassParm[User,String]("password", _.password, (d,v) => d.copy(password = v), None, 1)
      lazy val privilege: CaseClassParm[User,UserPrivilege] = CaseClassParm[User,UserPrivilege]("privilege", _.privilege, (d,v) => d.copy(privilege = v), Some(()=> UserPrivilege.Read), 2)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): User = {
        User(
          name = values(0).asInstanceOf[String],
          password = values(1).asInstanceOf[String],
          privilege = values(2).asInstanceOf[UserPrivilege],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): User = {
        val value =
          User(
            name = values.next().asInstanceOf[String],
            password = values.next().asInstanceOf[String],
            privilege = values.next().asInstanceOf[UserPrivilege],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(name: String, password: String, privilege: UserPrivilege): User =
        User(name, password, privilege)
    
    }
    
    
    lazy val typeName = "User"
  
  }
  
  
  
  
  trait MxLocusConfig {
  
    protected def jsonCodecBuilder(builder: a8.shared.json.JsonObjectCodecBuilder[LocusConfig,parameters.type]): a8.shared.json.JsonObjectCodecBuilder[LocusConfig,parameters.type] = builder
    
    implicit lazy val jsonCodec: a8.shared.json.JsonTypedCodec[LocusConfig,a8.shared.json.ast.JsObj] =
      jsonCodecBuilder(
        a8.shared.json.JsonObjectCodecBuilder(generator)
          .addField(_.protocol)
          .addField(_.proxyServerAddresses)
          .addField(_.anonymousSubnets)
          .addField(_.dataDirectory)
          .addField(_.s3)
          .addField(_.repos)
          .addField(_.users)
          .addField(_.noCacheFiles)
          .addField(_.port)
          .addField(_.versionsVersion)
          .addField(_.realm)
      )
      .build
    
    implicit val zioEq: zio.prelude.Equal[LocusConfig] = zio.prelude.Equal.default
    
    
    
    
    lazy val generator: Generator[LocusConfig,parameters.type] =  {
      val constructors = Constructors[LocusConfig](11, unsafe.iterRawConstruct)
      Generator(constructors, parameters)
    }
    
    object parameters {
      lazy val protocol: CaseClassParm[LocusConfig,String] = CaseClassParm[LocusConfig,String]("protocol", _.protocol, (d,v) => d.copy(protocol = v), Some(()=> "https"), 0)
      lazy val proxyServerAddresses: CaseClassParm[LocusConfig,Iterable[String]] = CaseClassParm[LocusConfig,Iterable[String]]("proxyServerAddresses", _.proxyServerAddresses, (d,v) => d.copy(proxyServerAddresses = v), Some(()=> Iterable("127.0.0.0/8")), 1)
      lazy val anonymousSubnets: CaseClassParm[LocusConfig,Iterable[String]] = CaseClassParm[LocusConfig,Iterable[String]]("anonymousSubnets", _.anonymousSubnets, (d,v) => d.copy(anonymousSubnets = v), Some(()=> Iterable.empty), 2)
      lazy val dataDirectory: CaseClassParm[LocusConfig,String] = CaseClassParm[LocusConfig,String]("dataDirectory", _.dataDirectory, (d,v) => d.copy(dataDirectory = v), None, 3)
      lazy val s3: CaseClassParm[LocusConfig,Option[S3Config]] = CaseClassParm[LocusConfig,Option[S3Config]]("s3", _.s3, (d,v) => d.copy(s3 = v), None, 4)
      lazy val repos: CaseClassParm[LocusConfig,Iterable[Repo]] = CaseClassParm[LocusConfig,Iterable[Repo]]("repos", _.repos, (d,v) => d.copy(repos = v), None, 5)
      lazy val users: CaseClassParm[LocusConfig,Iterable[User]] = CaseClassParm[LocusConfig,Iterable[User]]("users", _.users, (d,v) => d.copy(users = v), None, 6)
      lazy val noCacheFiles: CaseClassParm[LocusConfig,Iterable[CiString]] = CaseClassParm[LocusConfig,Iterable[CiString]]("noCacheFiles", _.noCacheFiles, (d,v) => d.copy(noCacheFiles = v), None, 7)
      lazy val port: CaseClassParm[LocusConfig,Int] = CaseClassParm[LocusConfig,Int]("port", _.port, (d,v) => d.copy(port = v), None, 8)
      lazy val versionsVersion: CaseClassParm[LocusConfig,String] = CaseClassParm[LocusConfig,String]("versionsVersion", _.versionsVersion, (d,v) => d.copy(versionsVersion = v), None, 9)
      lazy val realm: CaseClassParm[LocusConfig,String] = CaseClassParm[LocusConfig,String]("realm", _.realm, (d,v) => d.copy(realm = v), Some(()=> "Accur8 Repo"), 10)
    }
    
    
    object unsafe {
    
      def rawConstruct(values: IndexedSeq[Any]): LocusConfig = {
        LocusConfig(
          protocol = values(0).asInstanceOf[String],
          proxyServerAddresses = values(1).asInstanceOf[Iterable[String]],
          anonymousSubnets = values(2).asInstanceOf[Iterable[String]],
          dataDirectory = values(3).asInstanceOf[String],
          s3 = values(4).asInstanceOf[Option[S3Config]],
          repos = values(5).asInstanceOf[Iterable[Repo]],
          users = values(6).asInstanceOf[Iterable[User]],
          noCacheFiles = values(7).asInstanceOf[Iterable[CiString]],
          port = values(8).asInstanceOf[Int],
          versionsVersion = values(9).asInstanceOf[String],
          realm = values(10).asInstanceOf[String],
        )
      }
      def iterRawConstruct(values: Iterator[Any]): LocusConfig = {
        val value =
          LocusConfig(
            protocol = values.next().asInstanceOf[String],
            proxyServerAddresses = values.next().asInstanceOf[Iterable[String]],
            anonymousSubnets = values.next().asInstanceOf[Iterable[String]],
            dataDirectory = values.next().asInstanceOf[String],
            s3 = values.next().asInstanceOf[Option[S3Config]],
            repos = values.next().asInstanceOf[Iterable[Repo]],
            users = values.next().asInstanceOf[Iterable[User]],
            noCacheFiles = values.next().asInstanceOf[Iterable[CiString]],
            port = values.next().asInstanceOf[Int],
            versionsVersion = values.next().asInstanceOf[String],
            realm = values.next().asInstanceOf[String],
          )
        if ( values.hasNext )
           sys.error("")
        value
      }
      def typedConstruct(protocol: String, proxyServerAddresses: Iterable[String], anonymousSubnets: Iterable[String], dataDirectory: String, s3: Option[S3Config], repos: Iterable[Repo], users: Iterable[User], noCacheFiles: Iterable[CiString], port: Int, versionsVersion: String, realm: String): LocusConfig =
        LocusConfig(protocol, proxyServerAddresses, anonymousSubnets, dataDirectory, s3, repos, users, noCacheFiles, port, versionsVersion, realm)
    
    }
    
    
    lazy val typeName = "LocusConfig"
  
  }
}
