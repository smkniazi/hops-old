#!/bin/bash
rsync --delete -avze ssh conf/ cloud1:/home/steffeng/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf/ cloud2:/home/steffeng/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf/ cloud3:/home/steffeng/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf/ cloud4:/home/steffeng/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf/ cloud5:/home/steffeng/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf/ cloud6:/home/steffeng/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf/ cloud7:/home/steffeng/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud8:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud9:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud10:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud11:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud12:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud13:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud14:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud15:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud16:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud17:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
rsync --delete -avze ssh conf_opt/ cloud18:/opt/hop/hadoop-2.0.4-alpha/etc/hadoop
