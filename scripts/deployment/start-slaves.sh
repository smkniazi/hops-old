#!/bin/bash
ssh cloud1 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud1 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud2 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud2 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud3 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud3 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud4 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud4 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud5 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud5 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud6 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud6 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud7 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud7 "/home/steffeng/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
#ssh cloud8 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
#ssh cloud8 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud9 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud9 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud10 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud10 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud11 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud11 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud12 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud12 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud13 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud13 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud14 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud14 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud15 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud15 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud16 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud16 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud17 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud17 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &
ssh cloud18 "/opt/hop/hadoop-2.0.4-alpha/sbin/hadoop-daemon.sh start datanode" &
ssh cloud18 "/opt/hop/hadoop-2.0.4-alpha/sbin/yarn-daemon.sh start nodemanager" &


