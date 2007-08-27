-module(jrpc).

-export([
		 call/3,
         call/4,
         uicall/3,
         uicall/4,
         cast/3,
         event/2,
          
         rpc_loop/1 
]).

-export([test/0]).

call(Rcvr, Msg, Args) ->
    call0(call, Rcvr, Msg, Args, 5000).

call(Rcvr, Msg, Args, Timeout) ->
    call0(call, Rcvr, Msg, Args, Timeout).

uicall(Rcvr, Msg, Args) ->
    call0(uicall, Rcvr, Msg, Args, 5000).

uicall(Rcvr, Msg, Args, Timeout) ->
    call0(uicall, Rcvr, Msg, Args, Timeout).


call0(Kind, Rcvr, Msg, Args, Timeout) ->
        erlide_rex ! {Kind, Rcvr, Msg, Args, self()},
    receive
              {reply, Resp} ->
                  {ok, Resp};
                       Err ->
                           {error, Err}
             after Timeout ->
                 timeout
                   end.

cast(Rcvr, Msg, Args) ->
    erlide_rex ! {cast, Rcvr, Msg, Args},
    ok.

event(Id, Msg) ->
    erlide_rex ! {event, Id, Msg},
    ok.

rpc_loop(JavaNode) ->
    receive
       Msg ->
           {rex, JavaNode} ! Msg,
           rpc_loop(JavaNode)
       end,
    ok.

-define(P(X), io:format(">>> ~p~n", [X])).

test() ->
    	RT=org_erlide_jinterface_RpcTest,
        ?P(RT:test_str_arr(["1zx3"])),
        ?P(RT:test_str_arr([55, x, "123"])),
        
        
        ?P(RT:test_int(422)),
        
        ?P(RT:test_str("qwer")),
         
        {ok, E} = RT:new(),
        N = RT:square(E, 7),
        ?P(N),
        
        ?P(RT:test_int_arr([422])),
                
               
                 
        P = 'org.eclipse.core.runtime.Platform',
        io:format("---- ~p~n", [call(P, getApplicationArgs, [], 2000)]),
        io:format("---- ~p~n", [call(P, knownOSValues, [], 2000)]),
                
        U = 'org.eclipse.ui.PlatformUI',
        io:format("++++ ~p~n", [call(U, getWorkbench, [], 2000)]),
                        
    ok.