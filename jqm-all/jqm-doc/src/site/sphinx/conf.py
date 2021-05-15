# coding: utf-8

import os
on_rtd = os.environ.get('READTHEDOCS') == 'True'

source_suffix = '.rst'
source_encoding = 'utf-8'
master_doc = 'index'
exclude_dirnames = ['.git', '.svn']
extensions = ['sphinx.ext.todo',]

project = 'JQM'
copyright = '2012-2021, Enioka'
#release = '1.0'
hightlight_language = 'java'


if on_rtd:
    html_theme = 'default'
    html_theme_options = {
        'navigation_depth': 3,
    }
else:
    html_theme = 'bizstyle'
