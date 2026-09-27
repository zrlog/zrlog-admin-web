module.exports = {
    jest: {
        configure: (jestConfig) => {
            // CRA's Jest resolver predates the package exports used by Ant Design 6.
            jestConfig.moduleNameMapper = {
                ...jestConfig.moduleNameMapper,
                '^@rc-component/picker/(locale|generate)/(.*)$': '<rootDir>/node_modules/@rc-component/picker/lib/$1/$2',
                '^@ant-design/colors/es/(.*)$': '<rootDir>/node_modules/@ant-design/colors/lib/$1',
            };
            return jestConfig;
        },
    },
    webpack: {
        configure: (webpackConfig) => {
            webpackConfig.optimization.splitChunks = {
                chunks: 'all',
                maxSize: 2500000,
                cacheGroups: {
                    vendor: {
                        test: /[\\/]node_modules[\\/](react|react-dom|antd|react-router|react-router-dom|styled-components|@ant-design\/cssinjs)[\\/]/,
                        name: 'vendor',
                        chunks: 'all',
                    },
                },
            };
            webpackConfig.output = {
                ...webpackConfig.output,
                filename: 'static/js/[name].[contenthash:8].js',
                chunkFilename: 'static/js/[name].[contenthash:8].chunk.js',
            };
            return webpackConfig;
        },
    },
};
