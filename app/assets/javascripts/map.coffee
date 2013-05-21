define ['map'], () ->
  $ = jQuery
  FlightSight = window.FlightSight
  FlightSight.Map ?= {}

  $.fn.googleMap = () ->
    element = $(this).get(0)
    zoomLevel = $(this).data('zoom') || 12

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

        FlightSight.Map.map = new google.maps.Map(element, mapOptions)

        FlightSight.Map.polyOptions =
          strokeColor: "#f0f0f0"
          strokeOpacity: 1.0
          strokeWeight: 3

        FlightSight.Map.route = new google.maps.Polyline FlightSight.Map.polyOptions
        FlightSight.Map.route.setMap FlightSight.Map.map

        $(element).show()
    this

  FlightSight.Map.geopointToLatLng = (geopoint) ->
    new google.maps.LatLng(geopoint.coordinates[1], geopoint.coordinates[0])

  FlightSight.routePoints = new FlightSight.Collection

  FlightSight.routePoints.addPushHandler (flightpoint) ->
    latlng = FlightSight.Map.geopointToLatLng flightpoint.location
    flightpoint.latlng = latlng

    FlightSight.Map.route.getPath().push latlng
    FlightSight.Map.map.setCenter latlng

  FlightSight.pointsOfInterest = new FlightSight.Collection

  FlightSight.pointsOfInterest.addPushHandler (poi) ->
    latlng = FlightSight.Map.geopointToLatLng poi.loc
    infoWindow = new google.maps.InfoWindow {
      content: '<div id="content"><h1>' + poi.name + '</h1>'
    }
    marker = new google.maps.Marker {
      position: latlng
      map: FlightSight.Map.map
      title: poi.name
    }
    google.maps.event.addListener marker, 'click', () ->
      if FlightSight.Map.infoWindow?
        FlightSight.Map.infoWindow.close()
      FlightSight.Map.infoWindow = infoWindow
      infoWindow.open FlightSight.Map.map, marker


  window.FlightSight = FlightSight

  return $
