#!/bin/bash
source ~/.zshrc;cd /Users/zhuguojun/Documents/git_project/jobPromotion && mvn clean package -Denforcer.skip=true -DskipTests -Dspotbugs.skip=true
