Lucene Upgrader Release Process
===============================

This lives here as a cheat sheet, because releases are so infrequent that I
forget by the time I want to do it again.

Prerequisites:

* You have credentials to publish to the project on sonatype.
* You have a working GnuPG public/private key pair for signing the release.

Setup process:

(Once per project - still listed here so that I can find it for the next project
I have to set this up in.)

* Go into [Sonatype](https://s01.oss.sonatype.org/):
  * Click top-right menu: "Profile"
  * Click "User Token"
  * Click "Access User Token"
  * Copy the two values from there and put them somewhere safe
* In a local terminal:
  * Set env vars DEPLOY_USER and DEPLOY_PASS to the two values you just got
  * Test publishing a snapshot: `.\gradlew publish`

* In a local terminal:
    * `gpg --list-secret-keys --keyid-format=long`
    * Find the key ID you want to use in the output
    * `gpg --export-secret-keys --armor <key id>`

* In GitHub project:
  * Settings tab
  * Secrets section
  * New repository secret: DEPLOY_USER (value from earlier)
  * New repository secret: DEPLOY_PASS (value from earlier)
  * New repository secret: GPG_SECRET_KEY_ID (LAST 8 CHARS of secret key ID from earlier)
  * New repository secret: GPG_SECRET_KEY (output from running export-secret-keys)

* Borrow a `.github/workflows/gradle-publish.yml` from another project where it's
  already working, commit it and push to main branch

Release process:

**Open issue: Some or all of this could potentially be automated.**

1. Check that the version number you're about to use makes sense for the kind of changes
   documented in the changelog ([Semantic Versioning](https://semver.org/)).
2. Edit `build.gradle.kts` to update the version number.
3. Check that `CHANGES` includes the version number you're about to release,
   and the current date.
4. Commit those changes if you haven't already.
5. Push those changes to master
6. Tag the release version.
7. In GitHub:
   - In Releases, create a new release
   - Name the release with the version of the tag
   - Paste the changes for the version into the description
   - Save the new release. GitHub will automatically publish the release.

**TODO: CHECK EVERYTHING BELOW THIS POINT!**

1. Run:

    ```sh
    DEPLOY_USER=<YOUR USERNAME> DEPLOY_PASS=<YOUR PASSWORD> \
    GPG_USER=<YOUR GPG MAIL ADDRESS> \
    buildr release
    ```

2. Go to the [staging repository](https://oss.sonatype.org/#stagingRepositories)
   and manually inspect the repository contents.
3. Once satisfied that everything is present, Close the staging repository.
   All of their checks should pass at this point.
4. Smoke test the new artifacts by pointing some other build at the staging
   repository and checking that it still builds.
5. Release the staging repository, and the artifact should become visible
   in the public repository.
