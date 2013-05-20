define ['sources'], () ->
  init = ($, M) ->
    routeSource = new EventSource window.location + "source"

    routeSource.addEventListener "routePoint", (point) =>
      M.points.push point

      M.addPointToPath $.parseJSON point.data
      true

    routeSource.addEventListener "interestPoint", (poi) =>

    routeSource.onmessage = (e) ->
      console.log e


  init
