Lucene Upgrader Release Process
===============================

This lives here as a cheat sheet, because releases are so infrequent that I
forget by the time I want to do it again.

Prerequisites:

* You have credentials to publish to the project on sonatype.
* You have a working GnuPG public/private key pair for signing the release.

Process:

1. Check that the version number makes sense for the kind of changes documented
   in the changelog ([Semantic Versioning](https://semver.org/)).
2. Check that `CHANGES` includes the version number you're about to release,
   and the current date.
3. Commit those changes if you haven't already.
4. Run:

    ```sh
    DEPLOY_USER=<YOUR USERNAME> DEPLOY_PASS=<YOUR PASSWORD> \
    GPG_USER=<YOUR GPG MAIL ADDRESS> \
    buildr release
    ```

5. Go to the [staging repository](https://oss.sonatype.org/#stagingRepositories)
   and manually inspect the repository contents.
6. Smoke test the new artifacts by pointing some other build at the staging
   repository and checking that it still builds.
7. Once satisfied that everything is okay, Close the staging repository.
   All of their checks should pass, and the artifact should become visible
   in the public repository.
