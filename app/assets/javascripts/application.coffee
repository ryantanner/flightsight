console.log "starting application"
require ['map'], (args) ->
  $ = args[0]
  M = args[1]
  $('#map').googleMap(M)
  require ['sources'], (sources) ->
    sources($, M)
