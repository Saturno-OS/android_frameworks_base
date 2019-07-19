#!/usr/bin/env python3
#
# Copyright 2019, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
Unit tests for the compiler.py script.

Install:
  $> sudo apt-get install python3-pytest   ##  OR
  $> pip install -U pytest
See also https://docs.pytest.org/en/latest/getting-started.html

Usage:
  $> pytest compiler_test.py

See also https://docs.pytest.org/en/latest/usage.html
"""
import os

import compiler

DIR = os.path.abspath(os.path.dirname(__file__))
TEXTCACHE = os.path.join(DIR, 'test_fixtures/compiler/common_textcache')
SYSTRACE = os.path.join(DIR, 'test_fixtures/compiler/common_systrace')
ARGV = [os.path.join(DIR, 'compiler.py'), '-i', TEXTCACHE, '-t', SYSTRACE]

def assert_compile_result(output, expected, *extra_argv):
  argv = ARGV + ['-o', output] + [args for args in extra_argv]

  compiler.main(argv)

  with open(output, 'rb') as f1, open(expected, 'rb') as f2:
    assert f1.read() == f2.read()

def test_compiler_main(tmpdir):
  output = tmpdir.mkdir('compiler').join('output')

  # No duration
  expected = os.path.join(DIR,
                          'test_fixtures/compiler/test_result_without_duration.TraceFile.pb')
  assert_compile_result(output, expected)

  # 10ms duration
  expected = os.path.join(DIR,
                          'test_fixtures/compiler/test_result_with_duration.TraceFile.pb')
  assert_compile_result(output, expected, '--duration', '10')

  # 30ms duration
  expected = os.path.join(DIR,
                          'test_fixtures/compiler/test_result_without_duration.TraceFile.pb')
  assert_compile_result(output, expected, '--duration', '30')