function PendingUnitsCtrl($scope) {
    $scope.pendingUnits = [
        {src: 'dri-upload1', label: 'xyz.gpgz', size: '9.2GB', date: 'Fri, 4th October 2013'},
        {src: 'dri-upload1', label: 'abc.gpgz', size: '1.7GB', date: 'Fri, 4th October 2013'}
    ];
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