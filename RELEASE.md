# Lucene Upgrader Release Process

This lives here as a cheat sheet because releases are so infrequent that I
forget by the time I want to do it again.

## Prerequisites

* You have credentials to publish to the project on Maven Central.
* You have a working GnuPG public/private key pair for signing the release.

## One-time setup

(Once per project—still listed here so that I can find it for the next project
I have to set this up in.)

1. Locate a working GnuPG public/private key pair for signing the release.
   - In a local terminal:
      - `gpg --list-secret-keys --keyid-format=long`
      - Find the key ID you want to use in the output.
      - Export a copy for use on GitHub:
         - `gpg --export-secret-keys --armor (key id) > ~/.gnupg/my-secring.asc`
      - Export a copy for local testing:
         - `cat ~/.gnupg/my-secring.asc | gpg --dearmor > ~/.gnupg/my-secring.gpg`

2. Get a user token for publishing to Maven Central.
   - Go into [Maven Central Portal](https://central.sonatype.com/):
      - From the top right of the page, **Sign In**.
      - From the top right of the page, open the user menu, then **View Account**.
      - Click **Generate User Token**.
      - Copy **Username** and **Password** from there and put them somewhere safe.

3. Test publishing a snapshot.
   - Edit some values into your local user's `~/.gradle/gradle.properties`:
      ```
      mavenCentralUsername=(publishing username)
      mavenCentralPassword=(publishing password)
      signing.keyId=(the secret key ID)
      signing.password=(the password for the secret key)
      signing.secretKeyRingFile=/(path to home)/.gnupg/secring.gpg
      ```
   - `.\gradlew publishAllPublicationsToMavenCentralRepository`

4. Configure GitHub Actions.
   - In the GitHub project:
      - **Settings** tab
      - **Secrets** section
      - New repository secret: DEPLOY_USER (value from earlier)
      - New repository secret: DEPLOY_PASS (value from earlier)
      - New repository secret: GPG_SECRET_KEY_ID (LAST 8 CHARS of secret key ID from earlier)
      - New repository secret: GPG_SECRET_KEY (output from running export-secret-keys)
   - Borrow a `.github/workflows/gradle-publish.yml` from another project where it's
     already working, commit it and push to the main branch.

## Release process

**Open issue: Some or all of this could potentially be automated.**

1. Check that the version number you're about to use makes sense for the kind of changes
   documented in the changelog ([Semantic Versioning](https://semver.org/)).
2. Edit `build.gradle.kts` to update the version number.
   The version number should _not_ be a snapshot!
3. Check that `CHANGES` includes the version number you're about to release,
   and the current date.
4. Commit those changes if you haven't already.
5. Push those changes to master
6. Tag the release version.
7. Push the release tag to origin—GitHub will now start the build.
8. In GitHub:
   - In **Releases**, in the **Tags** tab, find the tag you just pushed.
   - "Create release" from that tag
      - Title is usually just the version number. 
      - Paste the changes for the version into the description
      - Publish the release
9. In Maven Central Portal:
   - Go to **Publish**
   - In the **Deployments** tab, find the deployment created by the build which
     just ran. It should be in "Validated" state.
   - Click **Publish**. It should move to "Publishing" state.
10. Bump to the next version number and put the "-SNAPSHOT" suffix back on.
11. Commit those changes.
12. Push those changes to master.
13. By now, the publishing should have finished, and you can verify
    that it is available in Maven Central.
