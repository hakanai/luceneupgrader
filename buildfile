
VERSION_NUMBER = '0.1'

repositories.remote << 'https://oss.sonatype.org/content/repositories/releases'
repositories.remote << 'http://mirrors.ibiblio.org/pub/mirrors/maven2/'

#LUCENE = artifact('org.trypticon.luceneupgrader.lucene4.internal.lucene:lucene-core:jar:4.10.4')

desc 'Lucene Index Upgrader'
define 'luceneupgrader' do
  project.version=VERSION_NUMBER
  project.group = 'org.trypticon.luceneupgrader'
  compile.options.source = compile.options.target = '1.7'
  #compile.with LUCENE
  package :jar
end
