
VERSION_NUMBER = "0.4.0"

repositories.remote << 'https://oss.sonatype.org/content/repositories/releases'
repositories.remote << 'https://repository.apache.org/content/repositories/releases'
repositories.remote << 'http://mirrors.ibiblio.org/pub/mirrors/maven2/'

repositories.release_to[:url] = 'https://oss.sonatype.org/service/local/staging/deploy/maven2/'
repositories.release_to[:username] = ENV['USERNAME']
repositories.release_to[:password] = ENV['PASSWORD']

LUCENE_VERSION = '7.5.0'

LUCENE = artifact("org.apache.lucene:lucene-core:jar:#{LUCENE_VERSION}")
LUCENE_RUNTIME = artifact("org.apache.lucene:lucene-backward-codecs:jar:#{LUCENE_VERSION}")

require 'buildr/custom_pom'

desc 'Lucene Index Upgrader'
define 'luceneupgrader' do
  project.version=VERSION_NUMBER
  project.group = 'org.trypticon.luceneupgrader'
  compile.options.source = compile.options.target = '1.8'
  compile.with LUCENE
  test.with 'junit:junit:jar:4.12'
  test.with 'org.hamcrest:hamcrest-library:jar:1.3'
  test.with LUCENE_RUNTIME
  package :jar
end
