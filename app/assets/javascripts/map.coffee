define ['map'], () ->
  $ = jQuery
  M = {}
  $.fn.googleMap = (M) ->
    element = $(this).get(0)
    zoomLevel = $(this).data('zoom') || 8

    if $(this).data('size')
      [width, height] = $(this).data('size').split('x')
      $(this).css({width: Number(width), height: Number(height), background: '#fff'})

    wrapperElem = $(this).wrap('<div class="map-wrap"/>').css({background:'#fff'})
    $(this).hide()

    geocoder = new google.maps.Geocoder

    geocoderParams =
      address: $(this).data('address') || "113 E Pecan St, San Antonio, 78205"
      region: "US"
    results = geocoder.geocode geocoderParams, (results, status) ->
      if status == google.maps.GeocoderStatus.OK
        latlng = results[0].geometry.location

        mapOptions =
          mapTypeControl: false
          overviewMapControl: false
          zoom: zoomLevel
          center: latlng
          mapTypeId: google.maps.MapTypeId.SATELLITE

        M.map = new google.maps.Map(element, mapOptions)

        M.polyOptions =
          strokeColor: "#f0f0f0"
          strokeOpacity: 1.0
          strokeWeight: 3

        M.route = new google.maps.Polyline M.polyOptions
        M.route.setMap M.map

        $(element).show()
    this

  M.geopointToLatLng = (geopoint) ->
    new google.maps.LatLng(geopoint.coordinates[1], geopoint.coordinates[0])

  M.addPointToPath = (flightpoint) ->
    path = M.route.getPath()
    latlng = M.geopointToLatLng flightpoint.location
    path.push latlng
    M.map.setCenter latlng

  return [$, M]
