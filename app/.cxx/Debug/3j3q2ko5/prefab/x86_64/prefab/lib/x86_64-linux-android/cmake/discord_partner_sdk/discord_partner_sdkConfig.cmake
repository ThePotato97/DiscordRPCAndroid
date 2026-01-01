if(NOT TARGET discord_partner_sdk::discord_partner_sdk)
add_library(discord_partner_sdk::discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk::discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION "C:/Users/potato/.gradle/caches/9.0-milestone-1/transforms/d1388bd90506d3385af99559e13da0c0/transformed/jetified-discord_partner_sdk/prefab/modules/discord_partner_sdk/libs/android.x86_64/libdiscord_partner_sdk.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/potato/.gradle/caches/9.0-milestone-1/transforms/d1388bd90506d3385af99559e13da0c0/transformed/jetified-discord_partner_sdk/prefab/modules/discord_partner_sdk/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

