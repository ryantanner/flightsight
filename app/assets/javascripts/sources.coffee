define ['sources'], () ->
  init = ($, M) ->
    routeSource = new EventSource window.location + "source"

    routeSource.addEventListener "flightPoint", (point) =>
      M.points.push point

      M.addPointToPath $.parseJSON point.data
      true

    routeSource.addEventListener "interestPoint", (poi) =>


  init
