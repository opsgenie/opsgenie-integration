package main

import (
    "fmt"
    "flag"
    "time"

    "k8s.io/client-go/kubernetes"
    "k8s.io/client-go/pkg/api/v1"
    "k8s.io/client-go/tools/clientcmd"
    "k8s.io/client-go/tools/cache"
    "k8s.io/client-go/pkg/fields"
)

var (
    kubeconfig = flag.String("kubeconfig", "./config", "absolute path to the kubeconfig file")
)

func main() {

    opsgenieUrl := "http://restapi3.apiary.io/notes"
    req.Header.Set("Content-Type", "application/json")


    flag.Parse()
    config, err := clientcmd.BuildConfigFromFlags("", *kubeconfig)
    if err != nil {
        panic(err.Error())
    }
    clientset, err := kubernetes.NewForConfig(config)
    if err != nil {
        panic(err.Error())
    }

    watchlist := cache.NewListWatchFromClient(clientset.Core().RESTClient(), "services", v1.NamespaceDefault,
        fields.Everything())
    _, controller := cache.NewInformer(
        watchlist,
        &v1.Service{},
        time.Second * 0,
        cache.ResourceEventHandlerFuncs{
            AddFunc: func(obj interface{}) {
                fmt.Printf("service added: %s \n", obj)
                req, err := http.NewRequest("POST", opsgenieUrl, obj)
            },
            DeleteFunc: func(obj interface{}) {
                fmt.Printf("service deleted: %s \n", obj)
                req, err := http.NewRequest("POST", opsgenieUrl, obj)
            },
            UpdateFunc:func(oldObj, newObj interface{}) {
                fmt.Printf("service changed \n")
                req, err := http.NewRequest("POST", opsgenieUrl, obj)
            },
        },
    )
    stop := make(chan struct{})
    go controller.Run(stop)
    for{
        time.Sleep(time.Second)
    }
}