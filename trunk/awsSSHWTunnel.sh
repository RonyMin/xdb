sudo ssh -i ./$1 -L 55500:$2:55500 -L 55501:$2:55501 ec2-user@$2