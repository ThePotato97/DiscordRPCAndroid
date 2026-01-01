cmake_minimum_required(VERSION 3.22.1)

project("discord-rpc-android")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Import the Discord Partner SDK
find_package(discord_partner_sdk REQUIRED CONFIG)

# Create a shared library
add_library(
        discord
        SHARED
        main.cpp)

# Link necessary libraries
target_link_libraries(
        discord
        android
        log
        discord_partner_sdk::discord_partner_sdk
)
