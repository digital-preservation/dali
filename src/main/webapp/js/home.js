var mPendingUnits = [
    {src: 'dri-upload1', label: 'xyz.gpgz', size: '9.2GB', date: 'Fri, 4th October 2013'},
    {src: 'dri-upload1', label: 'abc.gpgz', size: '1.7GB', date: 'Fri, 4th October 2013'}
];

function PendingUnitsCtrl($scope) {
//    $scope.pendingUnits = [
//        {src: 'dri-upload1', label: 'xyz.gpgz', size: '9.2GB', date: 'Fri, 4th October 2013'},
//        {src: 'dri-upload1', label: 'abc.gpgz', size: '1.7GB', date: 'Fri, 4th October 2013'}
//    ];
    $scope.pendingUnits = mPendingUnits;
}

function updatePending(fnUpdateModel) {
    var pendingUnitsElem = $("#pendingUnits");
    var scope = angular.element(pendingUnitsElem).scope();

    scope.$apply(function() {
        fnUpdateModel(mPendingUnits);
    });
}

$(document).ready(function() {
  "use strict";

  var socket = $.atmosphere;
  var subSocket;
  var transport = 'websocket';

  var request = {
    url: "/unit",
    contentType: "application/json",
    logLevel: 'debug',
    transport: transport,
    fallbackTransport: 'long-polling'
  };

  request.onOpen = function(response) {
    console.log('Atmosphere connected using ' + response.transport);

    transport = response.transport;

    if (response.transport == "local") {
      subSocket.pushLocal("Name?");
    }

    //request initial unit status
    subSocket.push(JSON.stringify({action: 'pending'}));

  };

  request.onReconnect = function(request, response) {
    socket.info("Reconnecting");
    console.log("Reconnecting...");
  };

  request.onMessage = function(response) {
    var message = response.responseBody;
      try {
          var json = JSON.parse(message);

          //is this a pending unit initial status
          if(json.pending) {
            updatePending(function(model) {
                model.length = 0; //empty the model
                $.each(json.pending.unit, function(i, v) {
                    model.push(v); //add to the model
                });
            });

          //is this an addition to the pending units
          } else if(json.pendingAdd) {
            updatePending(function(model) {
                $.each(json.pendingAdd.unit, function(i, v) {
                    model.push(v); //add to the model
                });
            });

          //is this a removal from the pending units
          } else if(json.pendingRemove) {
            updatePending(function(model) {
               $.each(model, function(i, v) {
                if(v.label == json.pendingRemove.unit.label) {
                    model.splice(i, 1);
                }
               });
            });
          }
      } catch (e) {
          console.log('Error: ', message.data);
          return;
      }

    console.info("Received message: " + json); //TODO should be debug
  };

  request.onClose = function(response) {
    console.log("Connection closed! " = response.responseBody)
  };

  request.onError = function(response) {
    console.log("Error: " + response.responseBody);
  };

  subSocket = socket.subscribe(request);
});