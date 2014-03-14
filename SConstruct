# SConstruct
# Written by Michael Barker and released to the public domain,
# as explained at http://creativecommons.org/publicdomain/zero/1.0/

import os
import xml.etree.ElementTree as ET

version = ET.parse('pom.xml').getroot().findtext('{http://maven.apache.org/POM/4.0.0}version')
version_directory = 'target/c/hdr_histogram-' + version
lib_directory     = version_directory + '/lib/'
bin_directory     = version_directory + '/bin/'
src_directory     = version_directory + '/src/'
test_directory    = version_directory + '/test/'
include_directory = version_directory + '/include/'

debug    = ARGUMENTS.get('debug', 0)
optimise = ARGUMENTS.get('optimise', 2)
cc       = ARGUMENTS.get('cc', 'clang')

env = Environment()
env['ENV']['PATH'] = os.environ['PATH']
env['CC']  = cc
env['CPATH'] = []
env['CFLAGS'] = ['-std=c99', '-Wall']
if cc == 'clang':
    env.Append(CFLAGS = '-fcolor-diagnostics')

if int(optimise) != 0:
    env.Append(CFLAGS = '-O' + str(optimise))
if int(debug):
    env.Append(CFLAGS = '-g')

bin = env.Clone()
bin['CPPDEFINES'] = ['__LZCNT__']
static_library = bin.StaticLibrary(lib_directory + 'hdr_histogram', Glob('src/main/c/*.c'))

tst = env.Clone()
tst['CPPPATH'] = ['src/main/c']
tst['LIBS'] = ['hdr_histogram.a']
tst['LIBPATH'] = [lib_directory]
tst['LINKFLAGS'] = ['-lm', '-lrt']
tst.Program(bin_directory + 'alltests', Glob('src/test/c/*_test.c'))

prf = tst.Clone();
prf.Append(CPPDEFINES = '_POSIX_C_SOURCE=199309L')
prf.Program(bin_directory + 'perftests', Glob('src/test/c/*_perf.c'))

exp = tst.Clone()
exp.Program(bin_directory + 'format_example', ['src/examples/c/hdr_histogram_format_example.c'])

env.Install(include_directory, Glob('src/main/c/*.h'))
env.Install(src_directory, Glob('src/main/c/*.c') + Glob('src/main/c/*.h'))
env.Install(test_directory, Glob('src/test/c/*.c') + Glob('src/test/c/*.h'))

env.Tar('target/c/hdr_histogram-' + version + '.tar.gz', Dir('hdr_histogram-' + version),
        TARFLAGS = ['-c', '-z', '-Ctarget/c'])
