File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
assert buildLog.text.contains ('\'one\' på engelsk er en')
