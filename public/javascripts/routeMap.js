(function () {
  "use strict";

  var evtSource, pointCount;

  evtSource = new EventSource(window.location + "route");

  evtSource.addEventListener("point", function(p) {
    pointCount = pointCount + 1;

    $("#count").text(pointCount);
  }, false);

}());
