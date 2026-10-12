cmake_minimum_required(VERSION 3.22.1)
project("goldenorg")

# استدعاء مكتبة Oboe
find_package(oboe REQUIRED CONFIG)

# إنشاء المحرك الصوتي الخاص بنا
add_library(goldenorg SHARED AudioEngine.cpp)

# ربط المحرك مع Oboe ونظام الأندرويد
target_link_libraries(goldenorg oboe::oboe log)