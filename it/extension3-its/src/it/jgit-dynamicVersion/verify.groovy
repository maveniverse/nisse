File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
assert buildLog.text.contains ('[INFO]  * ${nisse.jgit.dynamicVersion}=0.1.0')
