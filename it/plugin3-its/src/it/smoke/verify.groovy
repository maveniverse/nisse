File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()

File projectProperties = new File(basedir, 'project.properties')
assert projectProperties.text.contains ('nisse.file.one=en')
assert projectProperties.text.contains ('nisse.file.two=to')
