// Runs inside the YouTube frame: the outer IFrame API rounds custom rates.
(function () {
    var observedVideos = new WeakSet();

    function applySpeed(video) {
        var speed = AndroidBridge.getPlaybackSpeed();
        if (!Number.isFinite(speed) || speed < 0.5 || speed > 4) return;
        if (Math.abs(video.playbackRate - speed) > 0.001) {
            video.playbackRate = speed;
        }
    }

    function updateVideos() {
        document.querySelectorAll('video').forEach(function (video) {
            if (!observedVideos.has(video)) {
                observedVideos.add(video);
                // Reapply when YouTube resets the rate on seeking or loading.
                ['loadedmetadata', 'playing', 'ratechange'].forEach(function (event) {
                    video.addEventListener(event, function () { applySpeed(video); });
                });
            }
            applySpeed(video);
        });
    }

    // Also discovers replacement video elements and receives native speed changes.
    // The WebView destroys this timer when the player is disposed.
    setInterval(updateVideos, 250);
})();
