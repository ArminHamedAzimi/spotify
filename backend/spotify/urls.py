from rest_framework.routers import DefaultRouter

from .views import PlaylistFollowViewSet, PlaylistViewSet, SongViewSet, UserViewSet


router = DefaultRouter()
router.register("users", UserViewSet, basename="user")
router.register("songs", SongViewSet, basename="song")
router.register("playlists", PlaylistViewSet, basename="playlist")
router.register("playlist-follows", PlaylistFollowViewSet, basename="playlist-follow")

urlpatterns = router.urls
