
VERSION_NUMBER = "0.5.2-SNAPSHOT"

repositories.remote << 'https://oss.sonatype.org/content/repositories/releases'
repositories.remote << 'https://repository.apache.org/content/repositories/releases'
repositories.remote << 'http://mirrors.ibiblio.org/pub/mirrors/maven2/'

if VERSION_NUMBER =~ /SNAPSHOT/
  if ENV['DEPLOY_USER']
    repositories.release_to = { url: 'https://oss.sonatype.org/content/repositories/snapshots',
                                username: ENV['DEPLOY_USER'],
                                password: ENV['DEPLOY_PASS'] }
  end
  if ENV['GPG_USER']
    require 'buildr/gpg'
  end
else
  repositories.release_to = { url: 'https://oss.sonatype.org/service/local/staging/deploy/maven2',
                              username: ENV['DEPLOY_USER'],
                              password: ENV['DEPLOY_PASS'] }
  require 'buildr/gpg'
end

LUCENE_VERSION = '8.5.1'

require 'buildr/custom_pom'

desc 'Lucene Index Upgrader'
define 'luceneupgrader' do
  project.version=VERSION_NUMBER
  project.group = 'org.trypticon.luceneupgrader'

  pom.licenses['The Apache Software License, Version 2.0'] = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
  pom.scm_connection = pom.scm_developer_connection = 'scm:git:git@github.com:trejkaz/luceneupgrader'
  pom.scm_url = 'git@github.com:trejkaz/luceneupgrader'
  pom.url = 'https://github.com/trejkaz/luceneupgrader'
  pom.issues_url = 'https://github.com/trejkaz/luceneupgrader/issues'
  pom.issues_system = 'GitHub Issues'
  pom.add_developer('trejkaz', 'Trejkaz (pen name)', 'trejkaz@trypticon.org', ['Project Lead'])

  compile.options.source = compile.options.target = '1.8'
  compile.options.other = ['-encoding', 'UTF-8']
  compile.with 'com.google.code.findbugs:jsr305:jar:3.0.1'
  compile.with "org.apache.lucene:lucene-core:jar:#{LUCENE_VERSION}"
  javadoc.options['encoding'] = 'UTF-8'
  test.with 'junit:junit:jar:4.12'
  test.with 'org.hamcrest:hamcrest-library:jar:1.3'
  test.with "org.apache.lucene:lucene-backward-codecs:jar:#{LUCENE_VERSION}"
  package :jar
  package :sources
  package :javadoc
end
