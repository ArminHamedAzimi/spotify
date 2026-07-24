from rest_framework.routers import DefaultRouter

from .views import (
    ChatViewSet,
    PlaylistFollowViewSet,
    PlaylistViewSet,
    SongViewSet,
    UserViewSet,
)


router = DefaultRouter()
router.register("users", UserViewSet, basename="user")
router.register("songs", SongViewSet, basename="song")
router.register("playlists", PlaylistViewSet, basename="playlist")
router.register("playlist-follows", PlaylistFollowViewSet, basename="playlist-follow")
router.register("chat", ChatViewSet, basename="chat")

urlpatterns = router.urls
