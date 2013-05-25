define ['collections'], () ->

  FlightSight = window.FlightSight

  class Collection
    constructor: () ->
      this.coll = []
      this._onPush = []

      ###
    coll = []

    _onPush = []
    ###

    push: (obj) ->
      this.coll.push(obj)
      _.each this._onPush, (func) ->
        func(obj)

    addPushHandler: (func) ->
      this._onPush.push func

    last: () ->
      this.coll[this.coll.length - 1]

  FlightSight.Collection = Collection
