define ['sources'], () ->
  init = ($, M) ->
    evtSource = new EventSource window.location + "route"

    pointCount = 0

    evtSource.addEventListener "point", (point) =>
      pointCount++
      $("#count").text pointCount

      M.addPointToPath $.parseJSON point.data
      true

  init
