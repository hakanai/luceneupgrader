
VERSION_NUMBER = '0.2.1'

repositories.remote << 'https://oss.sonatype.org/content/repositories/releases'
repositories.remote << 'https://repository.apache.org/content/repositories/releases'
repositories.remote << 'http://mirrors.ibiblio.org/pub/mirrors/maven2/'

LUCENE_VERSION = '5.4.1'

LUCENE = artifact("org.apache.lucene:lucene-core:jar:#{LUCENE_VERSION}")
LUCENE_RUNTIME = artifact("org.apache.lucene:lucene-backward-codecs:jar:#{LUCENE_VERSION}")

desc 'Lucene Index Upgrader'
define 'luceneupgrader' do
  project.version=VERSION_NUMBER
  project.group = 'org.trypticon.luceneupgrader'
  compile.options.source = compile.options.target = '1.7'
  compile.with LUCENE
  test.with 'junit:junit:jar:4.12'
  test.with 'org.hamcrest:hamcrest-library:jar:1.3'
  test.with LUCENE_RUNTIME
  package :jar
end
