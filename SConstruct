# SConstruct
# Written by Michael Barker and released to the public domain,
# as explained at http://creativecommons.org/publicdomain/zero/1.0/

import os

env = Environment()
#env['ENV']['TERM'] = os.environ['TERM']
env['ENV']['PATH'] = os.environ['PATH']
env["CC"]  = "clang"
env["CPPPATH"] = []
env["CPPFLAGS"] = ['-g']

bin = env.Clone()
bin["CPPDEFINES"] = ['__LZCNT__']
library = bin.StaticLibrary('hdr_histogram', 'src/main/c/hdr_histogram.c')

tst = env.Clone()
tst["CPPPATH"] = ['src/main/c']
tst["LIBS"] = ['hdr_histogram']
tst["LIBPATH"] = ['.']
tst["LINKFLAGS"] = ['-lm']
tst.Program('alltests', ['src/test/c/hdr_histogram_test.c'])

exp = env.Clone()
exp["CPPPATH"] = ['src/main/c']
exp["LIBS"] = ['hdr_histogram']
exp["LIBPATH"] = ['.']
exp["LINKFLAGS"] = ['-lm']
exp.Program('format_example', ['src/examples/c/hdr_histogram_format_example.c'])
