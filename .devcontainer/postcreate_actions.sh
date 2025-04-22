#!/bin/zsh
cat /etc/*-release
echo "Git version  = $(git --version)"
echo "Java  version = $(java --version)"
echo "Mvn version  = $(mvn --version)"
# update theme
sed -i '/^ZSH_THEME/c\ZSH_THEME="agnoster"' ~/.zshrc
