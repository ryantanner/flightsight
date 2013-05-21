console.log "starting application"

window.FlightSight ?= {}

require ['collections'], () ->

require ['map'], ($) ->
  $('#map').googleMap()

require ['sources'], (sources) ->
  sources()

