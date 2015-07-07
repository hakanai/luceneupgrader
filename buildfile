
VERSION_NUMBER = '0.1'

repositories.remote << 'https://oss.sonatype.org/content/repositories/releases'
repositories.remote << 'https://repository.apache.org/content/repositories/releases'
repositories.remote << 'http://mirrors.ibiblio.org/pub/mirrors/maven2/'

LUCENE_VERSION = '5.2.1'

LUCENE = artifact('org.apache.lucene:lucene-core:jar:5.2.1')
LUCENE_RUNTIME = artifact('org.apache.lucene:lucene-backward-codecs:jar:5.2.1')

desc 'Lucene Index Upgrader'
define 'luceneupgrader' do
  project.version=VERSION_NUMBER
  project.group = 'org.trypticon.luceneupgrader'
  compile.options.source = compile.options.target = '1.7'
  compile.with LUCENE
  test.with LUCENE_RUNTIME
  package :jar
end
