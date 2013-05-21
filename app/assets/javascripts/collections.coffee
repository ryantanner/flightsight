define ['collections'], () ->

  FlightSight = window.FlightSight

  class Collection
    constructor: () ->

    coll: []

    _onPush: []

    push: (obj) ->
      @coll.push(obj)
      _.each @_onPush, (func) ->
        func(obj)

    addPushHandler: (func) ->
      @_onPush.push func


  FlightSight.Collection = Collection
