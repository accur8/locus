# Build Prerequisites
- Install Nix
- Configure ~/.a8/repo.properties:

```properties
repo_url=https://locus.accur8.io/repos/all
repo_realm=Accur8 Repo
repo_user=deployer
repo_password=<omitted>

publish_aws_access_key = <omitted>
publish_aws_secret_key = <omitted>
```

# Build/Deploy Instructions
```shell
nix-shell --command "./deploy.sh"
```


# we deploy artifacts to public maven so we can easily bootstrap infrastructure


