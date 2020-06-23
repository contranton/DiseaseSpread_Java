from matplotlib import pyplot as plt
import numpy as np

if __name__ == '__main__':
    print("Starting plot")
    data = []
    with open("data.csv", "r") as f:
        for line in f.readlines():            
            data.append(np.array([float(s) for s in line.strip()[:-1].split(";")]))

    # Extract time
    data = np.array(data)
    t = data[:, 0].copy()
    data = data[:,1:]

    # Show 100 random tracks, also highlight the 
    # top 20 most varied and most sick
    ind=np.arange(data.shape[1])[::data.shape[1]//100]
    ind1=np.argsort(data.std(axis=1))[:-20:-1]
    ind2=np.argsort(data.sum(axis=0))[:-20:-1]

    data = data[:, np.concatenate((ind1, ind2, ind))]


    # Plotem
    [plt.plot(t, data[:, i], linewidth=0.3) for i in range(data.shape[1])]

    plt.savefig("out.pdf")
    print("Finished plot")