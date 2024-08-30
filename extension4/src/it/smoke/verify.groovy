File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
assert buildLog.text.contains ('nisse.file.one=en')
assert buildLog.text.contains ('nisse.jgit.author=') // PR may change author
assert buildLog.text.contains ('nisse.os.bitness=64') // hopefully no 32 bit builds needed
