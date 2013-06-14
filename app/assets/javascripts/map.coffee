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

    FlightSight.Map.geocoder = new google.maps.Geocoder
    geocoder = FlightSight.Map.geocoder

    geocoderParams =
      address: $(this).data('address') || "113 E Pecan St, San Antonio, 78205"
      region: "US"
    results = geocoder.geocode geocoderParams, (results, status) ->
      if status == google.maps.GeocoderStatus.OK
        latlng = results[0].geometry.location

        mapOptions =
          mapTypeControl: true
          overviewMapControl: true
          zoom: zoomLevel
          center: latlng
          mapTypeId: google.maps.MapTypeId.SATELLITE

        FlightSight.Map.map = new google.maps.Map(element, mapOptions)

        FlightSight.Map.lastBounds = FlightSight.Map.map.getBounds()

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

  FlightSight.Map.latLngToGeoPoint = (latLng) ->
    {
      type: "Point",
      "coordinates": [ latLng.lng(), latLng.lat() ]
    }

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

  FlightSight.Map.statusUpdateFunc = () ->
    lastPoint = FlightSight.routePoints.last()
    if (lastPoint?)
      $('#altitude').text lastPoint['altitude']
      $('#groundspeed').text lastPoint['groundspeed']
      latlng = FlightSight.Map.geopointToLatLng lastPoint['location']

      FlightSight.Map.geocoder.geocode 'latLng' : latlng, (res, status) ->
        if (status == google.maps.GeocoderStatus.OK)
          if (res[1])
            $('#currentLocation').text(res[1].formatted_address)

      markerImage =
        url: '/assets/images/plane-icon.png',
        size: new google.maps.Size(128, 128),
        origin: new google.maps.Point(0, 0),
        anchor: new google.maps.Point(64, 64)

      if (FlightSight.Map.flightMarker?)
        FlightSight.Map.flightMarker.setMap(null)

      flightMarker = new google.maps.Marker {
        position: latlng
        map: FlightSight.Map.map
        icon: markerImage
      }

      FlightSight.Map.flightMarker

  FlightSight.Map.statsUpdateInterval = window.setInterval FlightSight.Map.statusUpdateFunc, 1000

  FlightSight.Map.boundsUpdateFunc = () ->
    currentBounds = FlightSight.Map.map.getBounds()
    if (FlightSight.Map.lastBounds?)
      if (not currentBounds.equals FlightSight.Map.lastBounds)
        FlightSight.Map.postBounds currentBounds
    FlightSight.Map.lastBounds = currentBounds

  FlightSight.Map.boundsUpdateInterval = window.setInterval FlightSight.Map.boundsUpdateFunc, 2000

  FlightSight.Map.postBounds = (bounds) ->
    $.ajax
      type: "POST",
      url: window.location.pathname + "bounds",
      dataType: "application/json",
      contentType: "application/json",
      data: JSON.stringify(
        [
          FlightSight.Map.latLngToGeoPoint(bounds.getNorthEast()),
          FlightSight.Map.latLngToGeoPoint(bounds.getSouthWest())
        ]
      ),
      success: (data) -> console.log(data)

  window.FlightSight = FlightSight

  return $
