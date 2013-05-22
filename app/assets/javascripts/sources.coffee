define ['sources'], () ->
  FlightSight = window.FlightSight

  init = () ->
    source = new EventSource window.location + "source"

    source.addEventListener "flightPoint", (point) =>
      obj = $.parseJSON point.data
      FlightSight.routePoints.push obj

    source.addEventListener "interestPoint", (poi) =>
      obj = $.parseJSON poi.data
      FlightSight.pointsOfInterest.push obj

  init
